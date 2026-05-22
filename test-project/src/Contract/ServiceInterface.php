<?php

namespace App\Contract;

interface ServiceInterface
{
    public function execute(string $input): string;
    public function getName(): string;
}
